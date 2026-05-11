package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.shaxowskiy.javaspeakerclub.dto.LectureResponse;
import ru.shaxowskiy.javaspeakerclub.dto.UserMediaUploadResponse;
import ru.shaxowskiy.javaspeakerclub.service.LectureService;
import ru.shaxowskiy.javaspeakerclub.service.UserMediaService;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final LectureService lectureService;
    private final UserMediaService userMediaService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserMediaUploadResponse uploadStandalone(@RequestPart("file") MultipartFile file) {
        return userMediaService.upload(file);
    }

    @PostMapping(value = "/upload/{lectureId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LectureResponse uploadMedia(
            @PathVariable UUID lectureId,
            @RequestPart("file") MultipartFile file) {
        return lectureService.uploadMedia(lectureId, file);
    }

    @GetMapping("/download/{lectureId}")
    public ResponseEntity<InputStreamResource> downloadMedia(@PathVariable UUID lectureId) {
        LectureService.MediaContent media = lectureService.getMedia(lectureId);
        InputStream stream = media.stream();
        String filename = media.filename();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/url/{lectureId}")
    public ResponseEntity<String> getPresignedUrl(@PathVariable UUID lectureId) {
        String url = lectureService.getMediaUrl(lectureId);
        return ResponseEntity.ok(url);
    }

    @DeleteMapping("/{lectureId}")
    public ResponseEntity<Void> deleteMedia(@PathVariable UUID lectureId) {
        lectureService.deleteMedia(lectureId);
        return ResponseEntity.noContent().build();
    }

}
