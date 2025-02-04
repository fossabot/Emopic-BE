package mmm.emopic.app.domain.photo.dto.response;

import lombok.Getter;
import mmm.emopic.app.domain.category.Category;
import mmm.emopic.app.domain.category.PhotoCategory;
import mmm.emopic.app.domain.diary.Diary;
import mmm.emopic.app.domain.emotion.Emotion;
import mmm.emopic.app.domain.emotion.PhotoEmotion;
import mmm.emopic.app.domain.emotion.dto.response.EmotionMainSubResponse;
import mmm.emopic.app.domain.emotion.dto.response.EmotionResponse;
import mmm.emopic.app.domain.photo.Photo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public class PhotoInformationResponse {

    private Long photoId;

    private String signedUrl;

    private String uploadDateTime;

    private Long diaryId;

    private String diaryContent;

    private List<String> categories;

    private EmotionMainSubResponse emotions;



    public PhotoInformationResponse(Photo photo, Diary diary, List<Category> categoryList, List<Emotion> emotionList){
        this.photoId = photo.getId();
        this.signedUrl = photo.getSignedUrl();
        this.diaryId = diary.getId();
        Optional<String> optionDiaryContent = Optional.ofNullable(diary.getContent());
        if(optionDiaryContent.isPresent()){
            this.diaryContent = diary.getContent();
        }
        else{
            this.diaryContent = "";
        }
        this.categories = categoryList.stream().map(Category::getName).collect(Collectors.toList());
        List<EmotionResponse> eRList = emotionList.stream().map(EmotionResponse::new).collect(Collectors.toList());
        this.emotions = new EmotionMainSubResponse(eRList);
        Optional<LocalDateTime> dateTime = Optional.ofNullable(photo.getSnapped_at());
        dateTime.ifPresent(localDateTime -> this.uploadDateTime = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}
