package com.ratana.task_management_api.task.event;

import com.ratana.task_management_api.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * The listener only delegates and isolates failures; notification rules are
 * covered by NotificationServiceImplTest.
 */
@ExtendWith(MockitoExtension.class)
class TaskEventListenerTest {

    @Mock
    private NotificationService notificationService;

    private TaskEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TaskEventListener(notificationService);
    }

    @Test
    @DisplayName("Delegates every task event to NotificationService")
    void handle_delegatesToNotificationService() {
        TaskEvent event = event();

        listener.handle(event);

        verify(notificationService).handleTaskEvent(event);
    }

    @Test
    @DisplayName("A notification failure is logged, never propagated")
    void handle_swallowsNotificationFailure() {
        TaskEvent event = event();
        doThrow(new IllegalStateException("Telegram down")).when(notificationService).handleTaskEvent(event);

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
    }

    private static TaskEvent event() {
        return new TaskEvent(TaskEventType.ASSIGNED, UUID.randomUUID(), "Write report", UUID.randomUUID(),
                UUID.randomUUID(), "Alice", UUID.randomUUID(), UUID.randomUUID(), null, null, null, null, null, null, null, null);
    }
}
