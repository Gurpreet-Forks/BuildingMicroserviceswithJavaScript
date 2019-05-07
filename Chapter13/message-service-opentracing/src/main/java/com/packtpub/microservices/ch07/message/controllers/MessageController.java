package com.packtpub.microservices.ch07.message.controllers;

import com.packtpub.microservices.ch07.message.MessageRepository;
import com.packtpub.microservices.ch07.message.clients.SocialGraphClient;
import com.packtpub.microservices.ch07.message.exceptions.MessageNotFoundException;
import com.packtpub.microservices.ch07.message.exceptions.MessageSendForbiddenException;
import com.packtpub.microservices.ch07.message.models.Message;
import com.packtpub.microservices.ch07.message.models.UserFriendships;
import io.micrometer.core.annotation.Timed;
import io.micrometer.statsd.StatsdMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@Timed
public class MessageController {

    @Autowired
    private MessageRepository messagesStore;

    @Autowired
    private SocialGraphClient socialGraphClient;

    @Autowired
    private StatsdMeterRegistry registry;

    @Timed(value="get.messages")
    @RequestMapping(path = "/{id}", method = RequestMethod.GET, produces = "application/json")
    public Message get(@PathVariable("id") String id) throws MessageNotFoundException {
        registry.counter("get_messages").increment();
        return messagesStore.get(id);
    }

    @RequestMapping(path = "/", method = RequestMethod.POST, produces = "application/json")
    public ResponseEntity<Message> send(@RequestBody Message message) throws MessageSendForbiddenException {

        List<String> friendships = socialGraphClient.getFriendships(message.getSender());
        if (!friendships.contains(message.getRecipient())) {
            throw new MessageSendForbiddenException("Must be friends to send message");
        }

        Message saved = messagesStore.save(message);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).build();
    }

    @Async
    public CompletableFuture<Boolean> isFollowing(String fromUser, String toUser) {

        String url = String.format(
                "http://localhost:4567/followings?user=%s&filter=%s",
                fromUser, toUser);

        RestTemplate template = new RestTemplate();
        UserFriendships followings = template.getForObject(url, UserFriendships.class);

        return CompletableFuture.completedFuture(
                followings.getFriendships().isEmpty()
        );
    }
}
