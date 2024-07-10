package com.fastcampus.pass.repository.notification;

import com.fastcampus.pass.repository.booking.BookingEntity;
import com.fastcampus.pass.util.LocalDateTimeUtils;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

public interface NotificationModelMapper {
    NotificationModelMapper INSTANCE = Mappers.getMapper(NotificationModelMapper.class);

    // 필드명이 같지 않거나 custom하게 매핑해주기
    @Mapping(target = "uuid", source = "bookingEntity.userEntity.uuid")
    @Mapping(target = "text", source = "bookingEntity.startedAt", qualifiedByName = "text")
    NotificationEntity toNotificationEntity(BookingEntity bookingEntity, NotificationEvent event);

    // 알람 보낼 메시지 생성
    @Named("text")
    default String text(LocalDateTime startedAt) {
        return String.format("안녕하세요. %s 수업 시작합니다. 수업 전 출석 체크 부탁드립니다. \uD830\uDE0A", LocalDateTimeUtils.format(startedAt));
    }

}
