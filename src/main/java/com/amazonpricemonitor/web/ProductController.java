package com.amazonpricemonitor.web;

import com.amazonpricemonitor.service.ProductCatalogService;
import com.amazonpricemonitor.web.dto.CreateProductRequest;
import com.amazonpricemonitor.web.dto.PriceHistoryPointResponse;
import com.amazonpricemonitor.web.dto.ProductResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductCatalogService productCatalogService;

    public ProductController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping
    public List<ProductResponse> listProducts() {
        return productCatalogService.listProducts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        return productCatalogService.createProduct(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productCatalogService.deleteProduct(id);
    }

    @GetMapping("/{id}/price-history")
    public List<PriceHistoryPointResponse> priceHistory(@PathVariable Long id) {
        return productCatalogService.priceHistory(id);
    }
}
