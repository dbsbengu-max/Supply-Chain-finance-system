package com.scf.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "md_sku", schema = "scf")
public class MdSku {

    @Id
    private String id;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    @Column(name = "spec", nullable = false)
    private String spec;

    @Column(name = "grade")
    private String grade;

    @Column(name = "origin")
    private String origin;

    @Column(name = "package_type")
    private String packageType;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "status", nullable = false)
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getPackageType() { return packageType; }
    public void setPackageType(String packageType) { this.packageType = packageType; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
