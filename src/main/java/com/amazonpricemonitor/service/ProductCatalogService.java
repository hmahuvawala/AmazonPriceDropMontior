package com.amazonpricemonitor.service;

import com.amazonpricemonitor.domain.MonitoredProduct;
import com.amazonpricemonitor.domain.PriceCheck;
import com.amazonpricemonitor.repository.MonitoredProductRepository;
import com.amazonpricemonitor.repository.PriceCheckRepository;
import com.amazonpricemonitor.web.dto.CreateProductRequest;
import com.amazonpricemonitor.web.dto.PriceHistoryPointResponse;
import com.amazonpricemonitor.web.dto.ProductResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCatalogService {

    private static final int HISTORY_LIMIT = 250;

    private final MonitoredProductRepository productRepository;
    private final PriceCheckRepository priceCheckRepository;

    public ProductCatalogService(
            MonitoredProductRepository productRepository, PriceCheckRepository priceCheckRepository) {
        this.productRepository = productRepository;
        this.priceCheckRepository = priceCheckRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts() {
        return productRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(this::toProductResponseWithLastPrice)
                .toList();
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        MonitoredProduct entity = new MonitoredProduct();
        entity.setAmazonUrl(request.getAmazonUrl().trim());
        entity.setDisplayName(request.getDisplayName().trim());
        entity.setThresholdPct(request.getThresholdPct());
        entity.setThresholdAmount(request.getThresholdAmount());
        entity.setActive(request.isActive());
        MonitoredProduct saved = productRepository.save(entity);
        return ProductResponse.fromEntity(saved);
    }

    private ProductResponse toProductResponseWithLastPrice(MonitoredProduct product) {
        Optional<PriceCheck> latest =
                priceCheckRepository.findFirstByProductIdAndSuccessIsTrueOrderByCreatedAtDesc(product.getId());
        if (latest.isEmpty()) {
            return ProductResponse.fromEntity(product);
        }
        PriceCheck row = latest.get();
        return ProductResponse.fromEntity(product, row.getPriceAmount(), row.getCurrency());
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new EntityNotFoundException("Product not found: " + id);
        }
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryPointResponse> priceHistory(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("Product not found: " + productId);
        }
        PageRequest page = PageRequest.of(0, HISTORY_LIMIT, Sort.by(Sort.Direction.ASC, "createdAt"));
        return priceCheckRepository.findByProductIdOrderByCreatedAtAsc(productId, page).stream()
                .map(PriceHistoryPointResponse::fromEntity)
                .toList();
    }
}
