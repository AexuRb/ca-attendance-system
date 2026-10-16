package com.ca.attendance.log;

import com.ca.attendance.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationActionTypesTest {
    @Test
    void acceptsTheActionAlreadyWrittenByAppearanceService() {
        assertThat(OperationActionTypes.normalizeFilter(" update_ui_appearance "))
                .isEqualTo("UPDATE_UI_APPEARANCE");
    }

    @Test
    void preservesExistingFiltersAndEmptySelection() {
        assertThat(OperationActionTypes.normalizeFilter("UPDATE_USER")).isEqualTo("UPDATE_USER");
        assertThat(OperationActionTypes.normalizeFilter(" ")).isNull();
    }

    @Test
    void stillRejectsUnknownActionTypes() {
        assertThatThrownBy(() -> OperationActionTypes.normalizeFilter("UNKNOWN_ACTION"))
                .isInstanceOf(ApiException.class);
    }
}
