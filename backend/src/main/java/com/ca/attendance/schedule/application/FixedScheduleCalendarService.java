package com.ca.attendance.schedule.application;

import com.ca.attendance.schedule.DutyScheduleService;
import com.ca.attendance.schedule.DutyScheduleSlotItem;
import com.ca.attendance.schedule.domain.FixedScheduleDay;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
public class FixedScheduleCalendarService {
    private final DutyScheduleService schedules;

    public FixedScheduleCalendarService(DutyScheduleService schedules) {
        this.schedules = schedules;
    }

    public FixedScheduleDay day(LocalDate date) {
        int weekday = date.getDayOfWeek().getValue();
        List<FixedScheduleDay.FixedSlot> slots = schedules.today(date).stream()
                .map(this::toSlot)
                .toList();
        return new FixedScheduleDay(date, weekday, weekdayName(weekday), slots);
    }

    public List<FixedScheduleDay> week(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<FixedScheduleDay> result = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            result.add(day(monday.plusDays(index)));
        }
        return List.copyOf(result);
    }

    private FixedScheduleDay.FixedSlot toSlot(DutyScheduleSlotItem slot) {
        return new FixedScheduleDay.FixedSlot(
                "slot-" + slot.id(),
                slot.id(),
                slot.startTime(),
                slot.endTime(),
                slot.title(),
                slot.location(),
                slot.note(),
                slot.assignees().stream()
                        .map(item -> new FixedScheduleDay.Assignee(
                                item.userId(), item.studentNo(), item.name(), item.sortOrder()))
                        .toList()
        );
    }

    private String weekdayName(int weekday) {
        return switch (weekday) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            case 7 -> "星期日";
            default -> "未知";
        };
    }
}
