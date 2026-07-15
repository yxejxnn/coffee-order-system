package com.coffeeorder.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coffeeorder.domain.member.repository.MemberRepository;
import com.coffeeorder.domain.menu.repository.MenuRepository;
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

	@Test
	void run_seedsMembersAndMenus_whenTablesAreEmpty() {
		when(memberRepository.count()).thenReturn(0L);
		when(menuRepository.count()).thenReturn(0L);

		new DataSeeder(memberRepository, menuRepository).run(null);

		verify(memberRepository).saveAll(any());
		verify(menuRepository).saveAll(any());
	}

	@Test
	void run_skipsSeeding_whenDataAlreadyExists() {
		when(memberRepository.count()).thenReturn(3L);
		when(menuRepository.count()).thenReturn(5L);

		new DataSeeder(memberRepository, menuRepository).run(null);

		verify(memberRepository, never()).saveAll(any());
		verify(menuRepository, never()).saveAll(any());
	}
}
