package org.kucherenkoos.carsharingservice.dto.car;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.kucherenkoos.carsharingservice.model.CarType;

@Getter
@Setter
@ToString
@Schema(description = "Request DTO for creating new car")
public class CreateCarRequestDto {

    @Schema(description = "Car model", example = "Cayman")
    @NotBlank
    private String model;

    @Schema(description = "Car brand", example = "Porsche")
    @NotBlank
    private String brand;

    @Schema(description = "Enum of car type", example = "COUPE")
    @NotNull
    private CarType carType;

    @Schema(description = "Number of available cars", example = "3")
    private int inventory;

    @Schema(description = "Daily fee for a car", example = "299.99")
    @NotNull
    private BigDecimal dailyFee;
}
