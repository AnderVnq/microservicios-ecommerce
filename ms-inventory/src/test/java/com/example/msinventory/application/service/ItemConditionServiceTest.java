package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.ItemConditionUseCase;
import com.example.msinventory.application.port.in.RegisterEntryCommand;
import com.example.msinventory.application.port.in.InventoryEntryUseCase;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.domain.exception.ConditionAlreadyExistsException;
import com.example.msinventory.domain.exception.ConditionInactiveException;
import com.example.msinventory.domain.exception.ConditionNotFoundException;
import com.example.msinventory.domain.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class ItemConditionServiceTest {

    @Autowired ItemConditionUseCase conditions;
    @Autowired InventoryEntryUseCase entries;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ActorProvider actorProvider;

    @AfterEach
    void cleanup() {
        jdbc.update("delete from inventory_movements");
        jdbc.update("delete from inventory_units");
        jdbc.update("delete from inventory_entries");
        jdbc.update("delete from item_conditions where code not in ('NEW','OPEN_BOX','REFURBISHED','USED')");
        jdbc.update("update item_conditions set active = true");
    }

    @Test
    void createDuplicate_conflict() {
        assertThatThrownBy(() -> conditions.create("new", "Otro"))
                .isInstanceOf(ConditionAlreadyExistsException.class);
    }

    @Test
    void create_uppercasesCodeAndIsActive() {
        ItemCondition c = conditions.create("grade_a", "Grado A");
        assertThat(c.getCode()).isEqualTo("GRADE_A");
        assertThat(c.isActive()).isTrue();
        assertThat(conditions.findAll()).extracting(ItemCondition::getCode).contains("GRADE_A", "NEW");
    }

    @Test
    void update_unknownCode_notFound() {
        assertThatThrownBy(() -> conditions.update("NOPE", "x", true))
                .isInstanceOf(ConditionNotFoundException.class);
    }

    @Test
    void deactivate_blocksNewEntries() {
        when(actorProvider.currentActor()).thenReturn("op-1");
        ItemCondition updated = conditions.update("USED", "Usado", false);
        assertThat(updated.isActive()).isFalse();

        RegisterEntryCommand cmd = new RegisterEntryCommand(EntryType.STOCK_PURCHASE, "SKU-1", "USED",
                Warehouse.USA, 1, new BigDecimal("10.00"), null, InventoryUnitStatus.AVAILABLE,
                null, null, null, null, null, null);
        assertThatThrownBy(() -> entries.register("k-1", "h", cmd))
                .isInstanceOf(ConditionInactiveException.class);
    }
}
