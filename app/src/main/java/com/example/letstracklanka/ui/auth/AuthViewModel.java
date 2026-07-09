package com.example.letstracklanka.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.example.letstracklanka.data.model.CustomerRequest;
import com.example.letstracklanka.data.repository.AuthRepository;

public class AuthViewModel extends ViewModel {
    private final AuthRepository repository = new AuthRepository();

    public LiveData<Boolean> performRegistration(String name, String email, String phone, String nic, String address) {
        CustomerRequest request = new CustomerRequest(name, email, phone, nic, address);
        return repository.registerCustomer(request);
    }
}