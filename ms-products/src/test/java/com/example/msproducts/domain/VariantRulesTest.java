package com.example.msproducts.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.msproducts.domain.model.DimensionUnit;
import com.example.msproducts.domain.model.Dimensions;
import com.example.msproducts.domain.model.VariantRules;
import com.example.msproducts.domain.model.Weight;
import com.example.msproducts.domain.model.WeightUnit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VariantRulesTest {

    @Test
    void weight_rejectsMoreThanFourDecimals() {
        assertThatThrownBy(() -> VariantRules.validateWeight(new Weight(new BigDecimal("1.00001"), WeightUnit.KG)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("decimales");
    }

    @Test
    void weight_rejectsValueThatRoundsToZeroKg() {
        // 0.0004 kg se guarda como 0.000 en weight_kg
        assertThatThrownBy(() -> VariantRules.validateWeight(new Weight(new BigDecimal("0.4"), WeightUnit.G)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("0 kg");
        assertThatThrownBy(() -> VariantRules.validateWeight(new Weight(new BigDecimal("0.0001"), WeightUnit.KG)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weight_acceptsSmallButStorableValues() {
        assertThatCode(() -> VariantRules.validateWeight(new Weight(new BigDecimal("0.0010"), WeightUnit.KG)))
                .doesNotThrowAnyException();
        assertThatCode(() -> VariantRules.validateWeight(new Weight(new BigDecimal("1.5000"), WeightUnit.LB)))
                .doesNotThrowAnyException();
    }

    @Test
    void dimensions_rejectsTooManyDecimalsOrZeroCm() {
        assertThatThrownBy(() -> VariantRules.validateDimensions(new Dimensions(new BigDecimal("1.12345"),
                BigDecimal.ONE, BigDecimal.ONE, DimensionUnit.CM)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("decimales");
        assertThatThrownBy(() -> VariantRules.validateDimensions(new Dimensions(BigDecimal.ONE,
                new BigDecimal("0.001"), BigDecimal.ONE, DimensionUnit.IN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("0 cm");
        assertThatCode(() -> VariantRules.validateDimensions(new Dimensions(new BigDecimal("0.01"),
                new BigDecimal("0.01"), new BigDecimal("0.01"), DimensionUnit.CM)))
                .doesNotThrowAnyException();
    }
}
