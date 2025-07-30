package com.zidong.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/redis")
public class RedisTestController {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/set")
    public String set() {
        redisTemplate.opsForValue().set("demo:key", "Hello Redis!", Duration.ofMinutes(5));
        return "Set OK";
    }

    @GetMapping("/get")
    public String get() {
        return redisTemplate.opsForValue().get("demo:key");
    }
}
