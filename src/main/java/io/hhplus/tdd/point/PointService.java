package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PointService {

  private static final long MAX_POINT = 100_000_000L;

  private final PointHistoryTable pointHistoryTable;
  private final UserPointTable userPointTable;

  public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
    this.userPointTable = userPointTable;
    this.pointHistoryTable = pointHistoryTable;
  }

  /**
   * 특정 유저의 포인트 조회
   */
  public UserPoint getPoint(long userId) {
    return userPointTable.selectById(userId);
  }

  /**
   * 특정 유저의 포인트 충전/이용 내역 조회
   */
  public List<PointHistory> getHistories(long userId) {
    return pointHistoryTable.selectAllByUserId(userId);
  }

  /**
   * 특정 유저의 포인트 충전
   */
  public UserPoint charge(long userId, long amount) {
    UserPoint current = userPointTable.selectById(userId);
    long newAmount = current.point() + amount;
    if (newAmount > MAX_POINT) {
      throw new IllegalStateException("최대 포인트 한도(1억)를 초과할 수 없습니다.");
    }

    UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);
    pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updated.updateMillis());
    return updated;
  }

  /**
   * 특정 유저의 포인트 사용
   */
  public UserPoint use(long userId, long amount) {
    UserPoint current = userPointTable.selectById(userId);
    if (current.point() < amount) {
      throw new IllegalStateException("포인트가 부족합니다.");
    }
    long newAmount = current.point() - amount;
    UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);
    pointHistoryTable.insert(userId, amount, TransactionType.USE, updated.updateMillis());
    return updated;
  }
}
