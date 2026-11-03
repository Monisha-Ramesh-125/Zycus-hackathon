package com.example.slotmind.engine;

import com.example.slotmind.entity.AppointmentType;
import com.example.slotmind.entity.Provider;
import com.example.slotmind.entity.Slot;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 1. the same provider's nearest open slot
 * 2. the nearest open slot with a different provider of the same specialty
 * 3. URGENT only: the nearest open slot with a provider of another specialty
 */
@Component
public class RuleBasedStrategy implements SchedulingStrategy {

    static final int MAX_OPTIONS = 3;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMM 'at' h:mm a", Locale.ENGLISH);

    @Override
    public String name() {
        return "rule";
    }

    @Override
    public List<Recommendation> recommend(SchedulingContext ctx) {
        var appointment = ctx.appointment();
        Provider original = appointment.getProvider();
        List<Slot> usable = UsableSlots.of(ctx);

        List<Recommendation> out = new ArrayList<>();
        for (Slot s : usable) {
            if (s.getProvider().getId().equals(original.getId())) {
                out.add(new Recommendation(s.getId(), null, 0.9,
                        "We can keep you with " + original.getName() + " on " + WHEN.format(s.getStartTime()) + "."));
            }
        }
        for (Slot s : usable) {
            Provider p = s.getProvider();
            if (!p.getId().equals(original.getId()) && p.getSpecialty() == original.getSpecialty()) {
                out.add(new Recommendation(s.getId(), p.getId(), 0.75,
                        original.getName() + " is not available, so we found " + p.getName()
                                + ", who offers the same kind of care, on " + WHEN.format(s.getStartTime()) + "."));
            }
        }
        if (appointment.getType() == AppointmentType.URGENT) {
            for (Slot s : usable) {
                Provider p = s.getProvider();
                if (!p.getId().equals(original.getId()) && p.getSpecialty() != original.getSpecialty()) {
                    out.add(new Recommendation(s.getId(), p.getId(), 0.4,
                            "Because this visit is urgent, the soonest opening is with " + p.getName() + " on "
                                    + WHEN.format(s.getStartTime())
                                    + ". They are a different type of doctor, so please confirm with the patient."));
                }
            }
        }
        return out.stream().limit(MAX_OPTIONS).toList();
    }
}
