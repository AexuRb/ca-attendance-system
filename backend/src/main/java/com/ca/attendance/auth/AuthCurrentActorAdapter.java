package com.ca.attendance.auth;

import com.ca.attendance.shared.application.CurrentActor;
import org.springframework.stereotype.Component;

@Component
public class AuthCurrentActorAdapter implements CurrentActor {
    @Override
    public Actor require() {
        AuthUser user = AuthContext.current();
        return new Actor(user.id(), user.studentNo(), user.name(), user.role());
    }
}
