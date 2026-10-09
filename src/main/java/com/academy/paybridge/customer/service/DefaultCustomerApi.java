package com.academy.paybridge.customer.service;

import com.academy.paybridge.customer.api.CustomerApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultCustomerApi implements CustomerApi {

}
