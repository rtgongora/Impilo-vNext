package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "msika_product_details")
public class ProductDetailEntity {

    @Id
    @Column(name = "item_id", length = 26)
    private String itemId;

    @Column(name = "form", length = 100)
    private String form;

    @Column(name = "strength", length = 100)
    private String strength;

    @Column(name = "route", length = 100)
    private String route;

    @Column(name = "uom", length = 50)
    private String uom;

    @Column(name = "pack_size")
    private Integer packSize;

    @Column(name = "barcode", length = 50)
    private String barcode;

    @Column(name = "batch_tracked", nullable = false)
    private boolean batchTracked = true;

    @Column(name = "expiry_tracked", nullable = false)
    private boolean expiryTracked = true;

    @Column(name = "cold_chain", nullable = false)
    private boolean coldChain = false;

    @Column(name = "controlled", nullable = false)
    private boolean controlled = false;

    @Column(name = "manufacturer", length = 255)
    private String manufacturer;

    @Column(name = "atc_code", length = 20)
    private String atcCode;

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }
    public String getStrength() { return strength; }
    public void setStrength(String strength) { this.strength = strength; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public Integer getPackSize() { return packSize; }
    public void setPackSize(Integer packSize) { this.packSize = packSize; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public boolean isBatchTracked() { return batchTracked; }
    public void setBatchTracked(boolean batchTracked) { this.batchTracked = batchTracked; }
    public boolean isExpiryTracked() { return expiryTracked; }
    public void setExpiryTracked(boolean expiryTracked) { this.expiryTracked = expiryTracked; }
    public boolean isColdChain() { return coldChain; }
    public void setColdChain(boolean coldChain) { this.coldChain = coldChain; }
    public boolean isControlled() { return controlled; }
    public void setControlled(boolean controlled) { this.controlled = controlled; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getAtcCode() { return atcCode; }
    public void setAtcCode(String atcCode) { this.atcCode = atcCode; }
}
