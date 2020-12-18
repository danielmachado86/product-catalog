package io.dmcapps.eda;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import javax.validation.Valid;

import static javax.transaction.Transactional.TxType.REQUIRED;

@ApplicationScoped
@Transactional(REQUIRED)
public class ProductService {

    public Product persistProduct(@Valid Product product) {

        Product.persist(product);
        return product;
    }
    
}
