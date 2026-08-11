package com.globalcodelabs.socialmediaplanner.interfaces.rest.controller;

import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;
import com.globalcodelabs.socialmediaplanner.interfaces.rest.response.PlatformResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/platforms")
public class PlatformController {

    @GetMapping
    public List<PlatformResponse> findAll() {
        return Arrays.stream(Platform.values())
                .map(PlatformResponse::from)
                .toList();
    }
}
