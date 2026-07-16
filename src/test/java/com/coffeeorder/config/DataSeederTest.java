package com.coffeeorder.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coffeeorder.domain.member.entity.Member;
import com.coffeeorder.domain.member.repository.MemberRepository;
import com.coffeeorder.domain.menu.repository.MenuRepository;
import com.coffeeorder.domain.point.repository.PointRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private MenuRepository menuRepository;

	@Mock
	private PointRepository pointRepository;

	@Test
	void run_seedsMembersMenusAndPoints_whenTablesAreEmpty() {
		when(memberRepository.count()).thenReturn(0L);
		when(menuRepository.count()).thenReturn(0L);
		when(memberRepository.saveAll(any())).thenReturn(List.of(new Member("홍길동"), new Member("김민준")));

		new DataSeeder(memberRepository, menuRepository, pointRepository).run(null);

		verify(memberRepository).saveAll(any());
		verify(menuRepository).saveAll(any());
		verify(pointRepository).saveAll(any());
	}

	@Test
	void run_skipsSeeding_whenDataAlreadyExists() {
		when(memberRepository.count()).thenReturn(3L);
		when(menuRepository.count()).thenReturn(5L);

		new DataSeeder(memberRepository, menuRepository, pointRepository).run(null);

		verify(memberRepository, never()).saveAll(any());
		verify(menuRepository, never()).saveAll(any());
		verify(pointRepository, never()).saveAll(any());
	}
}
