package org.apache.dubbo.samples.rest.impl;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.dubbo.samples.rest.api.User;
import org.apache.dubbo.samples.rest.api.UserService;

import org.springframework.stereotype.Service;

@Service("userService")
public class UserServiceImpl implements UserService {

    private final AtomicLong idGen = new AtomicLong();

    @Override
    public User getUser(Long id) {
        return new User(id, "username" + id);
    }


    @Override
    public Long registerUser(User user) {
//        System.out.println("Username is " + user.getName());
        return idGen.incrementAndGet();
    }
}
