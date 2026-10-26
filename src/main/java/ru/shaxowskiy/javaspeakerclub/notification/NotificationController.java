package ru.shaxowskiy.javaspeakerclub.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.notification.NotificationService.MarkReadRequest;
import ru.shaxowskiy.javaspeakerclub.notification.NotificationService.NotificationResponse;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> findMine() {
        return notificationService.findMine();
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(@RequestBody(required = false) MarkReadRequest request) {
        notificationService.markRead(request == null ? null : request.id());
        return ResponseEntity.noContent().build();
    }
}
