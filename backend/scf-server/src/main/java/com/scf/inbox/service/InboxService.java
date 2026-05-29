package com.scf.inbox.service;

import com.scf.common.exception.BusinessException;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.inbox.dto.InboxDtos.InboxEventView;
import com.scf.inbox.dto.InboxDtos.InboxFeedView;
import com.scf.inbox.dto.InboxDtos.InboxSummaryView;
import com.scf.inbox.entity.InboxEventRead;
import com.scf.inbox.repository.InboxEventReadRepository;
import com.scf.inbox.support.InboxEventAggregator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InboxService {

    private final TenantContext tenantContext;
    private final InboxEventAggregator aggregator;
    private final InboxEventReadRepository readRepository;

    public InboxService(
            TenantContext tenantContext,
            InboxEventAggregator aggregator,
            InboxEventReadRepository readRepository) {
        this.tenantContext = tenantContext;
        this.aggregator = aggregator;
        this.readRepository = readRepository;
    }

    @Transactional
    public InboxFeedView feed(String source, Boolean unreadOnly, int limit) {
        tenantContext.requirePermission("INBOX_VIEW");
        UserContext user = requireUser();
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();

        List<InboxEventView> events = aggregator.collect(operatorId, projectId, user, source);
        Set<String> readKeys = loadReadKeys(user.userId(), operatorId, projectId, events);
        List<InboxEventView> enriched = events.stream()
                .map(event -> withReadFlag(event, readKeys.contains(event.eventKey())))
                .filter(event -> unreadOnly == null || !unreadOnly || !event.read())
                .limit(Math.max(limit, 1))
                .toList();

        long unreadCount = events.stream().filter(event -> !readKeys.contains(event.eventKey())).count();
        InboxSummaryView summary = new InboxSummaryView(events.size(), unreadCount, countBySource(events));
        return new InboxFeedView(summary, enriched);
    }

    @Transactional
    public InboxEventView markRead(String eventKey) {
        tenantContext.requirePermission("INBOX_READ");
        UserContext user = requireUser();
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();

        readRepository
                .findByUserIdAndOperatorIdAndProjectIdAndEventKey(
                        user.userId(), operatorId, projectId, eventKey)
                .orElseGet(() -> {
                    InboxEventRead read = new InboxEventRead();
                    read.setId(IdGenerator.nextId());
                    read.setUserId(user.userId());
                    read.setOperatorId(operatorId);
                    read.setProjectId(projectId);
                    read.setEventKey(eventKey);
                    read.setReadAt(Instant.now());
                    return readRepository.save(read);
                });

        List<InboxEventView> events = aggregator.collect(operatorId, projectId, user, null);
        InboxEventView matched = events.stream()
                .filter(event -> event.eventKey().equals(eventKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "通知不存在或已失效", 404));
        return withReadFlag(matched, true);
    }

    private Set<String> loadReadKeys(
            String userId, String operatorId, String projectId, List<InboxEventView> events) {
        if (events.isEmpty()) {
            return Set.of();
        }
        List<String> keys = events.stream().map(InboxEventView::eventKey).toList();
        return new HashSet<>(readRepository.findReadEventKeys(userId, operatorId, projectId, keys));
    }

    private static Map<String, Long> countBySource(List<InboxEventView> events) {
        Map<String, Long> counts = new HashMap<>();
        for (InboxEventView event : events) {
            counts.merge(event.source(), 1L, Long::sum);
        }
        return counts;
    }

    private static InboxEventView withReadFlag(InboxEventView event, boolean read) {
        if (event.read() == read) {
            return event;
        }
        return new InboxEventView(
                event.eventKey(),
                event.source(),
                event.category(),
                event.severity(),
                event.title(),
                event.message(),
                event.businessType(),
                event.businessId(),
                event.businessLabel(),
                event.actionRoute(),
                event.occurredAt(),
                read,
                event.metadata());
    }

    private static UserContext requireUser() {
        UserContext user = SecurityUtils.currentUser();
        if (user == null) {
            throw new BusinessException("AUTH_401", "未登录", 401);
        }
        return user;
    }
}
