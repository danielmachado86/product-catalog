package io.dmcapps.eda;

import javax.persistence.Entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
public class Product extends PanacheEntity {

    public String name;
    public Brand brand;
    public Category category;
    public String picture;
    
    
}
