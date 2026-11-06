package edu.atria.capstone.repository;

import edu.atria.capstone.model.Holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

public class HolidayRepository {
    private final Map<LocalDate, Holiday> holidays = new HashMap<>();

    public void save(Holiday holiday) {
        if (holiday != null) {
            holidays.put(holiday.getDate(), holiday);
        }
    }

    public boolean isHoliday(LocalDate date) {
        if (date == null) return false;
        return holidays.containsKey(date);
    }

    public boolean isWeekend(LocalDate date) {
        if (date == null) return false;
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    public boolean isNonTradingDay(LocalDate date) {
        return isWeekend(date) || isHoliday(date);
    }

    public LocalDate getNextBusinessDay(LocalDate date) {
        if (date == null) return null;
        LocalDate next = date.plusDays(1);
        while (isNonTradingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    public List<Holiday> findAll() {
        return new ArrayList<>(holidays.values());
    }

    public void clear() {
        holidays.clear();
    }
}
