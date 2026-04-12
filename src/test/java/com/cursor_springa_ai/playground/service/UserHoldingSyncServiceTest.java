package com.cursor_springa_ai.playground.service;

import com.cursor_springa_ai.playground.model.entity.Instrument;
import com.cursor_springa_ai.playground.model.entity.User;
import com.cursor_springa_ai.playground.model.entity.UserHolding;
import com.cursor_springa_ai.playground.repository.UserHoldingRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserHoldingSyncServiceTest {

    @Test
    void replaceHoldings_deletesThenSaves() throws Exception {
        UserHoldingRepository repository = mock(UserHoldingRepository.class);
        UserHoldingSyncService service = new UserHoldingSyncService(repository);

        User user = new User("ZERODHA", "portfolio-1");
        setField(user, "id", 1L);
        Instrument instrument = new Instrument(123L, "INFY", "NSE", "INE009A01021");
        setField(instrument, "id", 10L);
        UserHolding holding = new UserHolding(
                user,
                instrument,
                "INFY",
                10,
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(1550),
                BigDecimal.valueOf(1600),
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(16000),
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(6.67),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        service.replaceHoldings(1L, List.of(holding));

        verify(repository).deleteByUserId(1L);
        verify(repository).flush();
        verify(repository).saveAll(List.of(holding));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
