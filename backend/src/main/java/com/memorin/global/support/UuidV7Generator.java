package com.memorin.global.support;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

import java.util.EnumSet;

/**
 * INSERT 직전에 UUID v7 값을 만들어 PK에 채워 넣는 Hibernate 제너레이터.
 *
 * <p>Java 기본 {@link java.util.UUID}는 완전 랜덤인 v4까지만 지원한다.
 * v7은 앞부분이 유닉스 타임스탬프라 생성 순서대로 정렬되며, 그 덕에
 * PK/인덱스 삽입 성능이 랜덤 UUID보다 좋다.
 *
 * <p>앱(Java)에서 값을 만들기 때문에 H2(로컬)·PostgreSQL 18(운영) 어디서든
 * 동일하게 동작한다. (DB {@code DEFAULT uuidv7()}에만 의존하면 H2에는 해당
 * 함수가 없어 테스트가 깨진다.)
 */
public class UuidV7Generator implements BeforeExecutionGenerator {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner,
                           Object currentValue, EventType eventType) {
        return GENERATOR.generate();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }
}
