package com.fastcampus.pass.job.statistics;

import com.fastcampus.pass.repository.booking.BookingEntity;
import com.fastcampus.pass.repository.statistics.StatisticsEntity;
import com.fastcampus.pass.repository.statistics.StatisticsRepository;
import com.fastcampus.pass.util.LocalDateTimeUtils;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class MakeStatisticsJobConfig {
    private final int CHUNK_SIZE = 10;

    private final StatisticsRepository statisticsRepository;

    public MakeStatisticsJobConfig(StatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }

    @Bean
    public Job makeStatisticsJob(JobRepository jobRepository, PlatformTransactionManager transactionManager, EntityManagerFactory entityManagerFactory, MakeDailyStatisticsTasklet makeDailyStatisticsTasklet, MakeWeeklyStatisticsTasklet makeWeeklyStatisticsTasklet) {
        Flow addStatisticsFlow = new FlowBuilder<Flow>("addStatisticsFlow")
                .start(addStatisticsStep(jobRepository, transactionManager, entityManagerFactory))
                .build();

        Flow makeDailyStatisticsFlow = new FlowBuilder<Flow>("makeDailyStatisticsFlow")
                .start(makeDailyStatisticsStep(jobRepository, makeDailyStatisticsTasklet, transactionManager))
                .build();

        Flow makeWeeklyStatisticsFlow = new FlowBuilder<Flow>("makeWeeklyStatisticsFlow")
                .start(makeWeeklyStatisticsStep(jobRepository, makeWeeklyStatisticsTasklet, transactionManager))
                .build();

        Flow parallelMakeStatisticsFlow = new FlowBuilder<Flow>("parallelMakeStatisticsFlow")
                .split(new SimpleAsyncTaskExecutor())
                .add(makeDailyStatisticsFlow, makeWeeklyStatisticsFlow)
                .build();

        return new JobBuilder("makeStatisticsJob", jobRepository)
                .start(addStatisticsFlow)
                .next(parallelMakeStatisticsFlow)
                .build()
                .build();
    }

    @Bean
    public Step addStatisticsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, EntityManagerFactory entityManagerFactory) {
        return new StepBuilder("addStatisticsStep", jobRepository)
                .<BookingEntity, BookingEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(addStatisticsItemReader(entityManagerFactory, null, null))
                .writer(addStatisticsItemWriter())
                .build();
    }

    @Bean
    @StepScope
    public JpaCursorItemReader<BookingEntity> addStatisticsItemReader(EntityManagerFactory entityManagerFactory, @Value("#{jobParameters[from]}") String fromString, @Value("#{jobParameters[to]}") String toString) {
        final LocalDateTime from = LocalDateTimeUtils.parse(fromString);
        final LocalDateTime to = LocalDateTimeUtils.parse(toString);

        return new JpaCursorItemReaderBuilder<BookingEntity>()
                .name("usePassesItemReader")
                .entityManagerFactory(entityManagerFactory)
                // JobParameter를 받아 종료 일시(endedAt) 기준으로 통계 대상 예약(Booking)을 조회합니다.
                .queryString("select b from BookingEntity b where b.endedAt between :from and :to")
                .parameterValues(Map.of("from", from, "to", to))
                .build();
    }

    @Bean
    public ItemWriter<BookingEntity> addStatisticsItemWriter() {
        return bookingEntities -> {
            Map<LocalDateTime, StatisticsEntity> statisticsEntityMap = new LinkedHashMap<>();

            for (BookingEntity bookingEntity : bookingEntities) {
                final LocalDateTime statisticsAt = bookingEntity.getStatisticsAt();
                StatisticsEntity statisticsEntity = statisticsEntityMap.get(statisticsAt);

                if (statisticsEntity == null) {
                    statisticsEntityMap.put(statisticsAt, StatisticsEntity.create(bookingEntity));

                } else {
                    statisticsEntity.add(bookingEntity);

                }

            }
            final List<StatisticsEntity> statisticsEntities = new ArrayList<>(statisticsEntityMap.values());
            statisticsRepository.saveAll(statisticsEntities);
            log.info("### addStatisticsStep 종료");

        };
    }

    @Bean
    public Step makeDailyStatisticsStep(JobRepository jobRepository, MakeDailyStatisticsTasklet makeDailyStatisticsTasklet, PlatformTransactionManager transactionManager) {
        return new StepBuilder("makeDailyStatisticsStep", jobRepository)
                .tasklet(makeDailyStatisticsTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step makeWeeklyStatisticsStep(JobRepository jobRepository, MakeWeeklyStatisticsTasklet makeWeeklyStatisticsTasklet, PlatformTransactionManager transactionManager) {
        return new StepBuilder("makeWeeklyStatisticsStep", jobRepository)
                .tasklet(makeWeeklyStatisticsTasklet, transactionManager)
                .build();
    }

}
