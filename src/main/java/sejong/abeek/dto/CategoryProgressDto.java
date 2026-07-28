package sejong.abeek.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryProgressDto {
    private final String category;
    private final double earnedCredits;
    private final double requiredCredits;
    private final boolean satisfied;
    private final String requirementSource;
}
