package io.dmcapps.eda;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "product")
public class ProductEntity {

    private Long id;
    private String name;
    private BrandEntity brand;
    private CategoryEntity category;
    private String picture;

    public ProductEntity() {
        //
    }
    
    @Id
    @SequenceGenerator(name = "productSeq", sequenceName = "product_id_seq", allocationSize = 1, initialValue = 1)
    @GeneratedValue(generator = "productSeq")
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    
    @Column
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @ManyToOne(cascade = {CascadeType.REMOVE}, targetEntity = BrandEntity.class)
    @JoinColumn(name="brand")
    public BrandEntity getBrand() {
        return brand;
    }

    public void setBrand(BrandEntity brand) {
        this.brand = brand;
    }

    @ManyToOne(cascade = {CascadeType.REMOVE}, targetEntity = CategoryEntity.class)
    @JoinColumn(name="category")
    public CategoryEntity getCategory() {
        return category;
    }

    public void setCategory(CategoryEntity category) {
        this.category = category;
    }

    @Column
    public String getPicture() {
        return picture;
    }
    public void setPicture(String picture) {
        this.picture = picture;
    }


    
}
