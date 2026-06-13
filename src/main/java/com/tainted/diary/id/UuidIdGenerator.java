package com.tainted.diary.id;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class UuidIdGenerator implements IdGenerator {
    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
