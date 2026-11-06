package edu.atria.capstone.model;

import java.time.LocalDate;
import java.util.Objects;

public class Holiday {
    private final LocalDate date;
    private final String holidayName;

    public Holiday(LocalDate date, String holidayName) {
        this.date = date;
        this.holidayName = holidayName;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getHolidayName() {
        return holidayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Holiday holiday)) return false;
        return Objects.equals(date, holiday.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date);
    }

    @Override
    public String toString() {
        return date + " - " + holidayName;
    }
}
