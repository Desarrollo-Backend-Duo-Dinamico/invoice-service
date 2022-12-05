package com.invoice.api.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.invoice.api.dto.ApiResponse;
import com.invoice.api.dto.DtoProduct;
import com.invoice.api.entity.Cart;
import com.invoice.api.entity.Invoice;
import com.invoice.api.entity.Item;
import com.invoice.api.repository.RepoCart;
import com.invoice.api.repository.RepoInvoice;
import com.invoice.api.repository.RepoItem;
import com.invoice.configuration.client.ProductClient;
import com.invoice.exception.ApiException;

@Service
public class SvcInvoiceImp implements SvcInvoice {
	private final double TAX_PERCENT = 0.16;

	@Autowired
	RepoCart repoCart;

	@Autowired
	RepoInvoice repo;
	
	@Autowired
	RepoItem repoItem;

	@Autowired
	ProductClient  productCl;

	@Override
	public List<Invoice> getInvoices(String rfc) {
		return repo.findByRfcAndStatus(rfc, 1);
	}

	@Override
	public List<Item> getInvoiceItems(Integer invoice_id) {
		return repoItem.getInvoiceItems(invoice_id);
	}

	@Override
	public ApiResponse generateInvoice(String rfc) {
		/*
		 * Requerimiento 5
		 * Implementar el método para generar una factura 
		 */
		//Recuperar carritos o devolver exception
		List<Cart> carritos_cliente = repoCart.findByRfcAndStatus(rfc, 1);
		if(carritos_cliente == null)
			throw new ApiException(HttpStatus.NOT_FOUND, "cart has no items");
		
		//genera items sin el id
		ArrayList<Item> bill_items = get_items(carritos_cliente);
		if (bill_items.isEmpty())
			throw new ApiException(HttpStatus.NOT_FOUND, "cart has no items");

		Invoice bill = get_invoice(bill_items);
		bill.setRfc(rfc);
		try {
			//necesitamos guardar la factura para poder asociarle los items
			repo.save(bill);
			repo.flush();
			bill = repo.findByRfcAndCreate_atAndTotal(rfc, bill.getCreated_at(), bill.getTotal());
			for (Item item : bill_items) {
				//Agregar bill id
				item.setId_invoice(bill.getInvoice_id());
				//actualizar stock
				productCl.updateProductStock(item.getGtin(), item.getQuantity());
				//Persisitmos los datos
				repoItem.save(item);
			}
			//Limpiamos el carrito
			repoCart.clearCart(rfc);
		} catch (Exception e) {
			if (bill != null)
				repo.delete(bill);
			throw new ApiException(HttpStatus.BAD_REQUEST, "some products are not longer avaible");
		}
		
		
		return new ApiResponse("invoice generated");
	}


	private ArrayList<Item> get_items(List<Cart> carritos){

		ArrayList<Item> bill_items = new ArrayList<Item>();

		for (Cart cart : carritos) {
			try {
				DtoProduct product_info = productCl.getProduct(cart.getGtin()).getBody();
				if (product_info == null)
					throw new ApiException(HttpStatus.BAD_REQUEST, "product with gtin: " +cart.getGtin() +" is not longer avaible ");

				Item new_item = new Item();
				new_item.setGtin(product_info.getGtin());
				new_item.setQuantity(cart.getQuantity());
				new_item.setUnit_price(product_info.getPrice());
				new_item.setTotal(new_item.getUnit_price() * new_item.getQuantity());
				new_item.setTaxes(new_item.getTotal() * this.TAX_PERCENT);
				new_item.setSubtotal(new_item.getTotal() - new_item.getTaxes());
				bill_items.add(new_item);
			} catch (Exception e) {
				e.printStackTrace();
				throw new ApiException(HttpStatus.NOT_FOUND, "some products are not avaible");
			}	
		}
		return bill_items;
	}

	private Invoice get_invoice(ArrayList<Item> items){
		Invoice bill = new Invoice();
		bill.setTotal(0.0);
		bill.setTaxes(0.0);
		bill.setSubtotal(0.0);

		for (Item item : items) {
			bill.setTotal(bill.getTotal() + item.getTotal());
			bill.setTaxes(bill.getTaxes() + item.getTaxes());
			bill.setSubtotal(bill.getTotal() + item.getSubtotal());
		}
		bill.setCreated_at(LocalDateTime.now());
		bill.setStatus(1);
		return bill;
	}
}
