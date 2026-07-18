package com.memorin.global.exception;

import com.memorin.domain.users.Entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

@Getter
public class UserDetailsImpl implements UserDetails {

    private final User  user;
    private final Collection<GrantedAuthority> authorities;


    public UserDetailsImpl(
            User user,
            Collection<GrantedAuthority> authorities
    ) {
        this.user = user;
        this.authorities = authorities;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }


    // 이름
    @Override
    public String getUsername() {
        return this.user.getUsername();
    }

    public UUID getUserId(){
        return this.user.getId();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetailsImpl.this.isAccountNonExpired();
    }
}
