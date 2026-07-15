package com.coffeeorder.config;

import com.coffeeorder.entity.Member;
import com.coffeeorder.entity.Menu;
import com.coffeeorder.repository.MemberRepository;
import com.coffeeorder.repository.MenuRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 회원가입/메뉴등록 API가 없는 발제 범위상, 초기 구동용 시드를 코드로 넣는다.
 * 이미 데이터가 있으면(재기동) 다시 넣지 않는다.
 */
@Component
public class DataSeeder implements ApplicationRunner {

	private final MemberRepository memberRepository;
	private final MenuRepository menuRepository;

	public DataSeeder(MemberRepository memberRepository, MenuRepository menuRepository) {
		this.memberRepository = memberRepository;
		this.menuRepository = menuRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (memberRepository.count() == 0) {
			memberRepository.saveAll(List.of(
					new Member("홍길동"),
					new Member("김민준"),
					new Member("이서연")
			));
		}

		if (menuRepository.count() == 0) {
			menuRepository.saveAll(List.of(
					new Menu("아메리카노", 4500),
					new Menu("카페라떼", 5000),
					new Menu("카푸치노", 5000),
					new Menu("바닐라라떼", 5500),
					new Menu("콜드브루", 5000)
			));
		}
	}
}
