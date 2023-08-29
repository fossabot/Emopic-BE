package mmm.emopic.app.domain.category.dto.response;

import lombok.Getter;
import mmm.emopic.app.domain.category.Category;

import java.util.ArrayList;
import java.util.List;

@Getter
public class CategoryDetailResponse {

    private  final List<CategoryDetail> categories = new ArrayList<>();
    @Getter
    public static class CategoryDetail{
        private final Long categoryId;
        private final String name;
        private final Long count;
        private final String thumbnail;
        public CategoryDetail(Category category, Long count){
            this.categoryId = category.getId();
            this.name = category.getName();
            this.thumbnail = category.getThumbnail();
            this.count = count;
        }

    }


    public void AddCategoryDetail(Category category, Long count){
        categories.add(new CategoryDetail(category,count));
    }
}
