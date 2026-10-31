package com.example.msproducts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.msproducts.domain.model.DimensionUnit;
import com.example.msproducts.domain.model.WeightUnit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WeightUnitTest {

    @Test
    void toKg_usesFixedFactors() {
        assertThat(WeightUnit.LB.toKg(BigDecimal.ONE)).isEqualByComparingTo("0.45359237");
        assertThat(WeightUnit.OZ.toKg(BigDecimal.ONE)).isEqualByComparingTo("0.028349523125");
        assertThat(WeightUnit.G.toKg(new BigDecimal("1500"))).isEqualByComparingTo("1.5");
        assertThat(WeightUnit.KG.toKg(new BigDecimal("2"))).isEqualByComparingTo("2");
    }

    @Test
    void toCm_usesFixedFactors() {
        assertThat(DimensionUnit.IN.toCm(BigDecimal.TEN)).isEqualByComparingTo("25.4");
        assertThat(DimensionUnit.CM.toCm(BigDecimal.TEN)).isEqualByComparingTo("10");
    }

    @Test
    void parse_isCaseInsensitive_andRejectsUnknown() {
        assertThat(WeightUnit.parse(" lb ")).isEqualTo(WeightUnit.LB);
        assertThat(DimensionUnit.parse("in")).isEqualTo(DimensionUnit.IN);
        assertThatThrownBy(() -> WeightUnit.parse("STONE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DimensionUnit.parse("FT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WeightUnit.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
