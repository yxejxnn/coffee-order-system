package com.coffeeorder.domain.member.repository;

import com.coffeeorder.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
