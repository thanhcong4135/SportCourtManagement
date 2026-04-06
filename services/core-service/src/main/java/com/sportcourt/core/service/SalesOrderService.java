package com.sportcourt.core.service;

import com.sportcourt.core.api.PageResponse;
import com.sportcourt.core.domain.Booking;
import com.sportcourt.core.domain.Product;
import com.sportcourt.core.domain.SalesOrder;
import com.sportcourt.core.domain.SalesOrderItem;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.domain.enums.SalesOrderStatus;
import com.sportcourt.core.dto.SalesOrderCreateRequest;
import com.sportcourt.core.dto.SalesOrderItemRequest;
import com.sportcourt.core.dto.SalesOrderItemResponse;
import com.sportcourt.core.dto.SalesOrderResponse;
import com.sportcourt.core.event.SalesOutboxService;
import com.sportcourt.core.repository.BookingRepository;
import com.sportcourt.core.repository.ProductRepository;
import com.sportcourt.core.repository.SalesOrderRepository;
import com.sportcourt.core.repository.VenueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final ProductRepository productRepository;
    private final BookingRepository bookingRepository;
    private final VenueRepository venueRepository;
    private final SalesOutboxService salesOutboxService;

    public SalesOrderService(SalesOrderRepository salesOrderRepository,
                             ProductRepository productRepository,
                             BookingRepository bookingRepository,
                             VenueRepository venueRepository,
                             SalesOutboxService salesOutboxService) {
        this.salesOrderRepository = salesOrderRepository;
        this.productRepository = productRepository;
        this.bookingRepository = bookingRepository;
        this.venueRepository = venueRepository;
        this.salesOutboxService = salesOutboxService;
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderCreateRequest req) {
        validateNoDuplicateProducts(req.items());
        Booking booking = loadBooking(req.bookingId());
        Venue venue = resolveVenue(req.venueId(), booking);
        UUID customerId = resolveCustomerId(req.customerId(), booking);

        Map<UUID, Product> productMap = loadProducts(req.items());
        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setBooking(booking);
        salesOrder.setVenue(venue);
        salesOrder.setCustomerId(customerId);
        salesOrder.setStatus(SalesOrderStatus.CREATED);
        salesOrder.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderItemRequest itemReq : req.items()) {
            Product product = productMap.get(itemReq.productId());
            if (!product.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product is inactive: " + product.getId());
            }
            if (!product.getVenue().getId().equals(venue.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product does not belong to target venue");
            }

            BigDecimal lineTotal = product.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            SalesOrderItem item = new SalesOrderItem();
            item.setProduct(product);
            item.setQuantity(itemReq.quantity());
            item.setUnitPrice(product.getUnitPrice());
            item.setLineTotal(lineTotal);
            salesOrder.addItem(item);
            total = total.add(lineTotal);
        }

        salesOrder.setTotalAmount(total);
        SalesOrder saved = salesOrderRepository.save(salesOrder);
        salesOutboxService.enqueueCreated(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> list(UUID bookingId,
                                                 UUID venueId,
                                                 UUID customerId,
                                                 OffsetDateTime from,
                                                 OffsetDateTime to,
                                                 Pageable pageable) {
        validateQueryRange(from, to);
        Specification<SalesOrder> spec = buildSpec(bookingId, venueId, customerId, from, to);
        Page<SalesOrderResponse> page = salesOrderRepository.findAll(spec, pageable).map(this::toResponse);
        return PageResponse.from(page);
    }

    private void validateNoDuplicateProducts(List<SalesOrderItemRequest> items) {
        Set<UUID> distinctProductIds = items.stream().map(SalesOrderItemRequest::productId).collect(Collectors.toSet());
        if (distinctProductIds.size() != items.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate productId in order items");
        }
    }

    private Booking loadBooking(UUID bookingId) {
        if (bookingId == null) {
            return null;
        }
        return bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private Venue resolveVenue(UUID requestedVenueId, Booking booking) {
        if (booking != null) {
            UUID venueIdFromBooking = booking.getCourt().getVenue().getId();
            if (requestedVenueId != null && !requestedVenueId.equals(venueIdFromBooking)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venueId must match booking venue");
            }
            return booking.getCourt().getVenue();
        }
        if (requestedVenueId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venueId is required when bookingId is null");
        }
        return venueRepository.findById(requestedVenueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
    }

    private UUID resolveCustomerId(UUID requestedCustomerId, Booking booking) {
        if (booking == null) {
            return requestedCustomerId;
        }
        if (booking.getCustomerId() != null && requestedCustomerId != null && !booking.getCustomerId().equals(requestedCustomerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId must match booking customer");
        }
        return booking.getCustomerId() != null ? booking.getCustomerId() : requestedCustomerId;
    }

    private Map<UUID, Product> loadProducts(List<SalesOrderItemRequest> items) {
        List<UUID> productIds = items.stream().map(SalesOrderItemRequest::productId).toList();
        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != productIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more products not found");
        }
        Map<UUID, Product> result = new HashMap<>();
        for (Product product : products) {
            result.put(product.getId(), product);
        }
        return result;
    }

    private Specification<SalesOrder> buildSpec(UUID bookingId,
                                                UUID venueId,
                                                UUID customerId,
                                                OffsetDateTime from,
                                                OffsetDateTime to) {
        Specification<SalesOrder> spec = Specification.where(null);
        if (bookingId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("booking").get("id"), bookingId));
        }
        if (venueId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("venue").get("id"), venueId));
        }
        if (customerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customerId"), customerId));
        }
        if (from != null) {
            OffsetDateTime normalizedFrom = from.withOffsetSameInstant(ZoneOffset.UTC);
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), normalizedFrom));
        }
        if (to != null) {
            OffsetDateTime normalizedTo = to.withOffsetSameInstant(ZoneOffset.UTC);
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), normalizedTo));
        }
        return spec;
    }

    private void validateQueryRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid query range");
        }
    }

    private SalesOrderResponse toResponse(SalesOrder salesOrder) {
        List<SalesOrderItemResponse> itemResponses = salesOrder.getItems().stream()
            .map(item -> new SalesOrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal()
            ))
            .toList();
        return new SalesOrderResponse(
            salesOrder.getId(),
            salesOrder.getBooking() != null ? salesOrder.getBooking().getId() : null,
            salesOrder.getVenue().getId(),
            salesOrder.getCustomerId(),
            salesOrder.getStatus(),
            salesOrder.getTotalAmount(),
            salesOrder.getCreatedAt(),
            itemResponses
        );
    }
}
