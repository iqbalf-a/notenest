package com.notenest.noteservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// @EnableFeignClients: memindai interface @FeignClient (lihat client/UserClient)
// dan membuatkan implementasinya saat runtime.
@SpringBootApplication
@EnableFeignClients
public class NoteServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NoteServiceApplication.class, args);
	}

}
