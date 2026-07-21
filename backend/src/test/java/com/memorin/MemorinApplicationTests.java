package com.memorin;

import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

// 실제 PostgreSQL 컨테이너로 부팅을 검증한다.
// 엔티티가 jsonb / 네이티브 ENUM / uuidv7() 등 Postgres 전용 기능에 의존하므로
// H2로는 SessionFactory 생성 자체가 불가능하다.
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemorinApplicationTests extends PostgresTestSupport {

	@Test
	void contextLoads() {
	}

}
