package com.example.service;

import com.example.mapper.OrderMapper;
import com.example.model.*;
import com.example.model.dtos.OrderDTO;
import com.example.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

@Service
public class OrderService
{

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final OrderMapper orderMapper;
    private final ProductRepository productRepository;
    private final EmailService emailService;

    private final CartEntryRepository cartEntryRepository;
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    public OrderService(OrderRepository orderRepository,UserRepository userRepository,CartRepository cartRepository,AddressRepository addressRepository,OrderMapper orderMapper,ProductRepository productRepository,EmailService emailService,CartEntryRepository cartEntryRepository)
    {
        this.orderRepository=orderRepository;
        this.userRepository=userRepository;
        this.cartRepository=cartRepository;
        this.orderMapper=orderMapper;
        this.productRepository=productRepository;
        this.emailService=emailService;
        this.cartEntryRepository=cartEntryRepository;

    }

    @Transactional
    public Order createOrder(OrderDTO orderDTO)
    {
        logger.info("Attempting to create order with details: {}", orderDTO);
        try {
            User user = userRepository.findById(orderDTO.getUserId())
                    .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + orderDTO.getUserId()));

            Order order = orderMapper.orderDTOToOrder(orderDTO);
            order.setOrderDate(LocalDate.now());
            order.setTotalPrice(orderDTO.getTotalPrice());

            order.setPaymentMethod(PaymentMethod.valueOf(orderDTO.getPaymentMethod()));
            if (orderDTO.getPaymentMethod() == null || PaymentMethod.valueOf(orderDTO.getPaymentMethod()) == null) {
                throw new IllegalArgumentException("Invalid or missing payment method.");
            }

            if (orderDTO.getDeliveryAddress() == 0) {
                orderDTO.setDeliveryAddress(user.getDefaultDeliveryAddress());
            }
            if (orderDTO.getInvoiceAddress() == 0) {
                orderDTO.setInvoiceAddress(user.getDefaultBillingAddress());
            }

            order.setDeliveryAddress(orderDTO.getDeliveryAddress());
            order.setInvoiceAddress(orderDTO.getInvoiceAddress());

            Order savedOrder = orderRepository.save(order);
            logger.info("Order saved with ID: {}", savedOrder.getId());

           //emailService.sendOrderConfirmationEmail(user.getEmail(), "Confirmare comandă", "Comanda ta cu numărul " + savedOrder.getId() + " a fost plasată cu succes.");

            updateProductQuantities(orderDTO.getCartId());


            Cart newCart = createNewCartFromOldCart(orderDTO.getCartId());

            clearCart(orderDTO.getCartId());
            logger.info("Cart cleared for Cart ID: {}", orderDTO.getCartId());

            return savedOrder;

        } catch (Exception e) {
            logger.error("Error creating order: {}", e.getMessage(), e);
            throw e;
        }
    }

    private Cart createNewCartFromOldCart(int oldCartId)
    {
        Cart oldCart = cartRepository.findById(oldCartId)
                .orElseThrow(() -> new EntityNotFoundException("Cart not found with id: " + oldCartId));


        Cart newCart = new Cart();
        newCart.setUser(oldCart.getUser());
        newCart.setTotalPrice(oldCart.getTotalPrice());

        Cart savedNewCart = cartRepository.save(newCart);

        for (CartEntry entry : oldCart.getCartEntries()) {
            CartEntry newEntry = new CartEntry();
            newEntry.setCart(savedNewCart);
            newEntry.setProduct(entry.getProduct());
            newEntry.setQuantity(entry.getQuantity());
            newEntry.setPricePerPiece(entry.getPricePerPiece());
            newEntry.setTotalPricePerEntry(entry.getTotalPricePerEntry());
            cartEntryRepository.save(newEntry);
        }

        return savedNewCart;
    }

    @Transactional
    public void updateProductQuantities(int cartId)
    {
        cartRepository.findById(cartId).ifPresent(cart -> {
            cart.getCartEntries().forEach(entry -> {
                Product product = entry.getProduct();
                int newQuantity = product.getAvailableQuantity() - entry.getQuantity();
                product.setAvailableQuantity(newQuantity);
                productRepository.save(product);
            });
            logger.info("Product quantities updated for Cart ID: {}", cartId);
        });
    }



    @Transactional
    public void clearCart(int cartId)
    {
        cartRepository.findById(cartId).ifPresent(cart ->
        {
            logger.info("Clearing cart with ID: {}", cartId);
            cart.setCartEntries(new ArrayList<>());
            cart.setTotalPrice(0);
            Cart updatedCart = cartRepository.save(cart);
            logger.info("Cart cleared: {}", updatedCart.getCartEntries().isEmpty());
        });
    }


    public Optional<Order> getOrderById(int id)
    {
        return orderRepository.findById(id);
    }

    public Order updateOrder(Order orderDetails, int id)
    {
        return orderRepository.findById(id).map(order ->
        {
            order.setUser(orderDetails.getUser());
            order.setCart(orderDetails.getCart());
            order.setPaymentMethod(orderDetails.getPaymentMethod());
            order.setDeliveryAddress(orderDetails.getDeliveryAddress());
            order.setInvoiceAddress(orderDetails.getInvoiceAddress());
            order.setTotalPrice(orderDetails.getTotalPrice());
            order.setOrderDate(orderDetails.getOrderDate());

            return orderRepository.save(order);
        }).orElseThrow(() -> new EntityNotFoundException("Order not found with id: " + id));
    }
    public void deleteOrder(int id)
    {
        if(orderRepository.existsById(id))
        {
            orderRepository.deleteById(id);
        }
        else
        {
            throw new EntityNotFoundException("Order not found with id:"+id);
        }
    }

}
