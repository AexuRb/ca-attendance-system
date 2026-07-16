package com.ca.attendance.shared.application;

import com.ca.attendance.common.Role;

public interface CurrentActor {
    Actor require();

    record Actor(long id, String studentNo, String name, Role role) {
    }
}
