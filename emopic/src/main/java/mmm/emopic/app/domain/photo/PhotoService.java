package mmm.emopic.app.domain.photo;


import lombok.RequiredArgsConstructor;
import mmm.emopic.app.domain.category.Category;
import mmm.emopic.app.domain.category.dto.response.CategoryResponse;
import mmm.emopic.app.domain.category.repository.CategoryRepository;
import mmm.emopic.app.domain.category.PhotoCategory;
import mmm.emopic.app.domain.category.repository.PhotoCategoryRepository;
import mmm.emopic.app.domain.diary.Diary;
import mmm.emopic.app.domain.diary.repository.DiaryRepository;
import mmm.emopic.app.domain.emotion.Emotion;
import mmm.emopic.app.domain.emotion.repository.EmotionRepository;
import mmm.emopic.app.domain.emotion.PhotoEmotion;
import mmm.emopic.app.domain.emotion.repository.PhotoEmotionRepository;
import mmm.emopic.app.domain.photo.dto.response.*;
import mmm.emopic.app.domain.photo.dto.request.PhotoUploadRequest;
import mmm.emopic.app.domain.photo.repository.PhotoRepository;
import mmm.emopic.app.domain.photo.repository.PhotoRepositoryCustom;
import mmm.emopic.app.domain.photo.support.*;
import mmm.emopic.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhotoService {
    private final PhotoRepository photoRepository;
    private final SignedURLGenerator signedURLGenerator;
    private final DiaryRepository diaryRepository;
    private final PhotoCategoryRepository photoCategoryRepository;
    private final PhotoEmotionRepository photoEmotionRepository;
    private final CategoryRepository categoryRepository;
    private final EmotionRepository emotionRepository;
    private final PhotoRepositoryCustom photoRepositoryCustom;
    private final PhotoInferenceWithAI photoInferenceWithAI;
    private final Translators translators;

    private final ImageUploader imageUploader;
    @Value("${DURATION}")
    private long duration;


    // 이미지 업로드 후 signed_url 반환하는 함수
    @Transactional
    public PhotoUploadResponse createPhoto(PhotoUploadRequest photoUploadRequest) {

        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));		//한국시간
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

        String fileName =  now.format(format) + userId.toString();
        // 이미지 업로드
        imageUploader.imageUpload(fileName, photoUploadRequest.getImage());

        // 다운로드용 signed_url 생성
        String signedUrl = getSignedUrl(fileName).orElseThrow(() -> new RuntimeException("create signed url error"));
        String thumbnailSignedUrl = getSignedUrl("thumbnail/"+fileName).orElseThrow(() -> new RuntimeException("create signed url error"));;
        // signed_url 만료시간 설정
        LocalDateTime signedUrlExpiredTime = LocalDateTime.now().plusMinutes(duration);
        LocalDateTime thumbnailSignedUrlExpiredTime = LocalDateTime.now().plusMinutes(duration);

        // 캡션 요청하기
        String caption = requestCaption(signedUrl);

        // photo 객체 만들어서 저장하기
        Photo photo = Photo.builder()
                .name(fileName)
                .caption(caption)
                .signedUrl(signedUrl)
                .signedUrlExpireTime(signedUrlExpiredTime)
                .tbSignedUrl(thumbnailSignedUrl)
                .tbSignedUrlExpireTime(thumbnailSignedUrlExpiredTime)
                .build();

        Photo savedPhoto = photoRepository.save(photo);

        // caption 내용 일기장에 저장하기
        Diary diary = Diary.builder().photo(savedPhoto).content(caption).build();
        diaryRepository.save(diary);

        // categories 요청하기
        requestCategories(savedPhoto);

        return new PhotoUploadResponse(savedPhoto.getId(),thumbnailSignedUrl);
    }
    // 새로운 카테고리 만들기
    @Transactional
    public Category createCategory(String name){
        Category category = Category.builder().name(name).build();
        return categoryRepository.save(category);
    }
    @Transactional
    public void requestCategories(Photo photo){

        CategoryInferenceResponse categoryInferenceResponse = photoInferenceWithAI.getClassificationsByPhoto(photo.getSignedUrl()).orElseThrow(() -> new RuntimeException("classification 과정에서 오류 발생"));

        List<String> requiredTranslateResult = categoryInferenceResponse.getCategories();
        List<String> result = new ArrayList<>();
        for(String translateText :requiredTranslateResult){
            result.add(translators.papagoTranslate(translateText));
        }
        for(String categoryName : result){
            Category category = categoryRepository.findByName(categoryName).orElseGet(() -> createCategory(categoryName));
            PhotoCategory photoCategory = PhotoCategory.builder().photo(photo).category(category).build();
            photoCategoryRepository.save(photoCategory);
        }
    }
    // 캡셔닝 내용을 AI inference 서버에서 받아오는 함수
    public String requestCaption(String signedUrl) {
        CaptionInferenceResponse caption = photoInferenceWithAI.getCaptionByPhoto(signedUrl).orElseThrow(() -> new RuntimeException("captioning 과정에서 오류 발생"));
        String result = caption.getCaption();
        return translators.deeplTranslate(result);
    }

    // Optional 사용방법 https://www.daleseo.com/java8-optional-effective/
    public Optional<String> getSignedUrl(String fileName){
        try {
            return Optional.of(signedURLGenerator.generateV4GetObjectSignedUrl(fileName));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public PhotoInformationResponse getPhotoInformation(Long photoId) {
        Photo photo = photoRepository.findById(photoId).orElseThrow(() -> new ResourceNotFoundException("photo", photoId));

        Optional<Diary> optionalDiary = diaryRepository.findByPhotoId(photoId);
        Diary diary;
        if(optionalDiary.isEmpty()){
            diary = Diary.builder().photo(photo).build();
            diary = diaryRepository.save(diary);
        }
        else{
            diary = optionalDiary.get();
        }
        List<PhotoCategory> photoCategoryList= photoCategoryRepository.findByPhotoId(photoId);
        List<Category> categories = new ArrayList<>();
        for(PhotoCategory photoCategory : photoCategoryList){
            Long cid = photoCategory.getCategory().getId();
            categories.add(categoryRepository.findById(cid).orElseThrow(() -> new ResourceNotFoundException("category",cid )));
        }
        List<PhotoEmotion> photoEmotionList = photoEmotionRepository.findByPhotoId(photoId);
        List<Emotion> emotions = new ArrayList<>();
        for(PhotoEmotion photoEmotion : photoEmotionList){
            Long eid = photoEmotion.getEmotion().getId();
            emotions.add(emotionRepository.findById(eid).orElseThrow(() -> new ResourceNotFoundException("emotion",eid )));
        }
        return new PhotoInformationResponse(photo, diary, categories, emotions);
    }

    @Transactional
    public Page<PhotoInCategoryResponse> getPhotoInCategory(Long categoryId, Pageable pageable){
        Page<Photo> photoList = photoRepositoryCustom.findByCategoryId(categoryId, pageable);
        List<PhotoInCategoryResponse> results = new ArrayList<>();
        for(Photo photo : photoList){
            List<PhotoEmotion> photoEmotionList = photoEmotionRepository.findByPhotoId(photo.getId());
            List<Emotion> emotions = new ArrayList<>();
            for(PhotoEmotion photoEmotion : photoEmotionList){
                Long eid = photoEmotion.getEmotion().getId();
                emotions.add(emotionRepository.findById(eid).orElseThrow(() -> new ResourceNotFoundException("emotion",eid )));
            }
            results.add(new PhotoInCategoryResponse(photo, emotions));
        }

        return new PageImpl<>(results,pageable, photoList.getTotalElements());
    }

    public PageResponse getPhotosInformation(Pageable pageable){
        Page<Photo> photoList = photoRepositoryCustom.findAllPhotos(pageable);
        List<PhotosInformationResponse> photosInformationResponseList = new ArrayList<>();
        for(Photo photo: photoList) {
            List<PhotoCategory> photoCategoryList = photoCategoryRepository.findByPhotoId(photo.getId());
            List<Category> categories = new ArrayList<>();
            for (PhotoCategory photoCategory : photoCategoryList) {
                Long cid = photoCategory.getCategory().getId();
                categories.add(categoryRepository.findById(cid).orElseThrow(() -> new ResourceNotFoundException("category", cid)));
            }
            List<PhotoEmotion> photoEmotionList = photoEmotionRepository.findByPhotoId(photo.getId());
            List<Emotion> emotions = new ArrayList<>();
            for (PhotoEmotion photoEmotion : photoEmotionList) {
                Long eid = photoEmotion.getEmotion().getId();
                emotions.add(emotionRepository.findById(eid).orElseThrow(() -> new ResourceNotFoundException("emotion", eid)));
            }
            photosInformationResponseList.add(new PhotosInformationResponse(photo,categories,emotions));
        }
        return new PageResponse(new PageImpl<>(photosInformationResponseList,photoList.getPageable(),photoList.getTotalElements()));
    }
}
