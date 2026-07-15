package com.coffeeorder.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coffeeorder.repository.MemberRepository;
import com.coffeeorder.repository.MenuRepository;
import com.coffeeorder.repository.OrdersRepository;
import com.coffeeorder.repository.PointHistoryRepository;
import com.coffeeorder.repository.PointRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntityMappingTest {

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private MenuRepository menuRepository;

	@Autowired
	private PointRepository pointRepository;

	@Autowired
	private PointHistoryRepository pointHistoryRepository;

	@Autowired
	private OrdersRepository ordersRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void member_point_isOneToOne_uniquePerMember() {
		Member member = memberRepository.save(new Member("테스트회원"));
		pointRepository.save(new Point(member));
		entityManager.flush();

		assertThatThrownBy(() -> {
			pointRepository.saveAndFlush(new Point(member));
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void pointHistory_mapsToMemberAndRecordsAmount() {
		Member member = memberRepository.save(new Member("테스트회원"));

		PointHistory history = pointHistoryRepository.save(
				new PointHistory(member, PointHistoryType.CHARGE, 10_000L, null));
		entityManager.flush();
		entityManager.clear();

		PointHistory found = pointHistoryRepository.findById(history.getId()).orElseThrow();
		assertThat(found.getMember().getId()).isEqualTo(member.getId());
		assertThat(found.getType()).isEqualTo(PointHistoryType.CHARGE);
		assertThat(found.getAmount()).isEqualTo(10_000L);
		assertThat(found.getCreatedAt()).isNotNull();
	}

	@Test
	void orders_snapshotsUnitPriceAndEnforcesUniqueOrderGroupId() {
		Member member = memberRepository.save(new Member("테스트회원"));
		Menu menu = menuRepository.save(new Menu("아메리카노", 4500));
		String orderGroupId = "11111111-1111-1111-1111-111111111111";

		ordersRepository.save(new Orders(member, menu, 2, 4500, 9000L, orderGroupId));
		entityManager.flush();

		assertThatThrownBy(() -> {
			ordersRepository.saveAndFlush(new Orders(member, menu, 1, 4500, 4500L, orderGroupId));
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void pointHistory_type_isStoredAsVarchar_notNativeEnum() {
		Object dataType = entityManager
				.createNativeQuery("SELECT DATA_TYPE FROM information_schema.COLUMNS "
						+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'point_history' AND COLUMN_NAME = 'type'")
				.getSingleResult();

		assertThat(dataType).isEqualTo("varchar");
	}
}
