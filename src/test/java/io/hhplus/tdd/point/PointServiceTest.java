package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PointServiceTest {

  private UserPointTable userPointTable;
  private PointHistoryTable pointHistoryTable;
  private PointService pointService;

  @BeforeEach
  void setUp() {
    userPointTable = mock(UserPointTable.class);
    pointHistoryTable = mock(PointHistoryTable.class);
    pointService = new PointService(userPointTable, pointHistoryTable);
  }

  @Test
  @DisplayName("특정 유저의 포인트를 조회할 수 있다")
  void getPoint_ReturnsUserPoint() {
    // given
    long userId = 1L;
    UserPoint expected = new UserPoint(userId, 10_000L, System.currentTimeMillis());
    when(userPointTable.selectById(userId)).thenReturn(expected);

    // when
    UserPoint actual = pointService.getPoint(userId);

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @DisplayName("특정 유저의 포인트 충전/이용 내역을 조회할 수 있다")
  void getHistories_ReturnsPointHistories() {
    // given
    long userId = 1L;
    List<PointHistory> expected = List.of(
        new PointHistory(1L, userId, 10_000L, TransactionType.CHARGE, System.currentTimeMillis())
    );
    when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(expected);

    // when
    List<PointHistory> result = pointService.getHistories(userId);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @DisplayName("특정 유저의 포인트를 충전할 수 있다")
  void charge_IncreasesUserPoint() {
    // given
    long userId = 1L;
    long current = 5_000L;
    long charge = 10_000L;
    long expected = 15_000L;

    UserPoint before = new UserPoint(userId, current, System.currentTimeMillis());
    UserPoint after = new UserPoint(userId, expected, System.currentTimeMillis());

    when(userPointTable.selectById(userId)).thenReturn(before);
    when(userPointTable.insertOrUpdate(userId, expected)).thenReturn(after);

    // when
    UserPoint result = pointService.charge(userId, charge);

    // then
    assertThat(result.point()).isEqualTo(expected);
    verify(pointHistoryTable).insert(eq(userId), eq(charge), eq(TransactionType.CHARGE), anyLong());
  }

  @Test
  @DisplayName("포인트 충전 시 최대 포인트 한도(1억)를 초과하면 예외가 발생한다")
  void charge_ThrowsIfOverMaxPoint() {
    // given
    long userId = 1L;
    long current = 100_000_000L;
    long amount = 1L;

    when(userPointTable.selectById(userId)).thenReturn(new UserPoint(userId, current, System.currentTimeMillis()));

    // expect
    assertThatThrownBy(() -> pointService.charge(userId, amount))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("최대 포인트 한도");
  }

  @Test
  @DisplayName("특정 유저의 포인트를 사용할 수 있다")
  void use_DecreasesUserPoint() {
    // given
    long userId = 1L;
    long current = 10_000L;
    long use = 4_000L;
    long expected = 6_000L;

    UserPoint before = new UserPoint(userId, current, System.currentTimeMillis());
    UserPoint after = new UserPoint(userId, expected, System.currentTimeMillis());

    when(userPointTable.selectById(userId)).thenReturn(before);
    when(userPointTable.insertOrUpdate(userId, expected)).thenReturn(after);

    // when
    UserPoint result = pointService.use(userId, use);

    // then
    assertThat(result.point()).isEqualTo(expected);
    verify(pointHistoryTable).insert(eq(userId), eq(use), eq(TransactionType.USE), anyLong());
  }

  @Test
  @DisplayName("포인트가 부족하면 사용 시 예외가 발생한다")
  void use_ThrowsIfInsufficientPoint() {
    // given
    long userId = 1L;
    long current = 1_000L;
    long amount = 2_000L;

    when(userPointTable.selectById(userId)).thenReturn(new UserPoint(userId, current, System.currentTimeMillis()));

    // expect
    assertThatThrownBy(() -> pointService.use(userId, amount))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("포인트가 부족");
  }
}
