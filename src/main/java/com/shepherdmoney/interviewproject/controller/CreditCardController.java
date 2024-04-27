package com.shepherdmoney.interviewproject.controller;

import com.shepherdmoney.interviewproject.model.BalanceHistory;
import com.shepherdmoney.interviewproject.model.CreditCard;
import com.shepherdmoney.interviewproject.model.User;
import com.shepherdmoney.interviewproject.repository.BalanceHistoryRepository;
import com.shepherdmoney.interviewproject.repository.CreditCardRepository;
import com.shepherdmoney.interviewproject.repository.UserRepository;
import com.shepherdmoney.interviewproject.vo.request.AddCreditCardToUserPayload;
import com.shepherdmoney.interviewproject.vo.request.UpdateBalancePayload;
import com.shepherdmoney.interviewproject.vo.response.CreditCardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
public class CreditCardController {


    @Autowired
    CreditCardRepository creditCardRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BalanceHistoryRepository balanceHistoryRepository;

    @PostMapping("/credit-card")
    public ResponseEntity addCreditCardToUser(@RequestBody AddCreditCardToUserPayload payload) {

        Optional<User> data=userRepository.findById(payload.getUserId());
        User user= data.orElse(null);
        CreditCard creditCard=new CreditCard();
        if (user!=null){

            creditCard.setOwner(user.getId());
            creditCard.setNumber(payload.getCardNumber());
            creditCard.setIssuanceBank(payload.getCardIssuanceBank());

            try{
                if(creditCardRepository.findByNumber(creditCard.getNumber())==null){
                    creditCard=creditCardRepository.save(creditCard);
                    List<String> creditCardList=user.getCreditCardList()!=null?user.getCreditCardList():new ArrayList<>();

                    creditCardList.add(creditCard.getNumber());
                    user.setCreditCardList(creditCardList);
                    userRepository.save(user);
                }
                else{
                    return new ResponseEntity<>("Given credit card already exists",HttpStatus.BAD_REQUEST);
                }
            } catch (Exception e) {

                return new ResponseEntity<>("Something went wrong contact support team",HttpStatus.BAD_REQUEST);
            }
        }else{
            return new ResponseEntity<>("User not found",HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(creditCard.getId(), HttpStatus.OK);
    }

    @GetMapping("/credit-card:all")
    public ResponseEntity<List<CreditCardView>> getAllCardOfUser(@RequestParam int userId) {

        List<CreditCardView> creditCardViews=new ArrayList<>();
        try{
        List<String> creditCardList=userRepository.findById(userId).get().getCreditCardList();
        if(creditCardList!=null&& !creditCardList.isEmpty()) {
            for (String creditCardNumber : creditCardList) {
                CreditCard creditCard = creditCardRepository.findByNumber(creditCardNumber);
                creditCardViews.add(new CreditCardView(creditCard.getIssuanceBank(), creditCard.getNumber()));
            }
        }
        } catch (Exception e) {
            return new ResponseEntity<>(creditCardViews,HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(creditCardViews,HttpStatus.OK);
    }

    @GetMapping("/credit-card:user-id")
    public ResponseEntity getUserIdForCreditCard(@RequestParam String creditCardNumber) {

        Optional<User> user;
        CreditCard creditCard;
        try{
            creditCard=creditCardRepository.findByNumber(creditCardNumber);
            if(creditCard!=null){
                user=userRepository.findById(creditCard.getOwner());
                if(user.isEmpty()) return new ResponseEntity<>("User not found",HttpStatus.BAD_REQUEST);
            }else{
                return new ResponseEntity<>("Credit Card not found",HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            return new ResponseEntity<>("Something went wrong contact support team",HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(user.get().getId(),HttpStatus.OK);
    }

    @PostMapping("/credit-card:update-balance")
    public ResponseEntity postMethodName(@RequestBody UpdateBalancePayload[] payload) {

        List<ResponseEntity> response=new ArrayList<>();
        for (UpdateBalancePayload payload1 : payload) {
            CreditCard creditCard=creditCardRepository.findByNumber(payload1.getCreditCardNumber());

            if(creditCard!=null){
                List<BalanceHistory> balanceHistoryList=balanceHistoryRepository.findByCreditCardNumber(creditCard.getNumber());

                TreeMap<LocalDate,BalanceHistory>balanceHistoryTreeMap=getbalanceHistoryTreeMap(balanceHistoryList);

                BalanceHistory balanceHistory=getClosestBalanceRecord(balanceHistoryTreeMap,payload1.getBalanceDate());

                if(balanceHistory!=null) {

                    if(!balanceHistory.getDate().isEqual(payload1.getBalanceDate())){

                        balanceHistory=createBalanceHistory(balanceHistory.getBalance(), payload1.getBalanceDate(),
                                creditCard.getNumber());

                        balanceHistoryTreeMap.put(balanceHistory.getDate(),balanceHistory);

                    }

                    double balanceDifference=payload1.getBalanceAmount()-balanceHistory.getBalance();
                    if (balanceDifference != 0) {

                        for(BalanceHistory i: balanceHistoryTreeMap.values()){
                            //updates the balances whose dates are equal or after the given payload date
                            if (i.getDate().isAfter(payload1.getBalanceDate()) || i.getDate().isEqual(payload1.getBalanceDate()))
                            {
                                i.setBalance(i.getBalance()+balanceDifference);
                            }
                            else{
                                break;
                            }
                        }
                    }
                }
                else{

                    balanceHistory=createBalanceHistory(payload1.getBalanceAmount(), payload1.getBalanceDate(),creditCard.getNumber() );

                    balanceHistoryTreeMap.put(balanceHistory.getDate(),balanceHistory);

                }
                try{
                    balanceHistoryList=balanceHistoryRepository.saveAll(balanceHistoryTreeMap.values());
                    creditCard.setBalanceHistoryList(balanceHistoryList);

                } catch (Exception e) {
                    response.add( new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST));
                    continue;
                }

            }else{
                response.add(new ResponseEntity<>("Credit Card Not Found",HttpStatus.BAD_REQUEST));
            }
            response.add(new ResponseEntity<>("Credit Card Updated Successfully",HttpStatus.OK));
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
        }


    public TreeMap<LocalDate,BalanceHistory> getbalanceHistoryTreeMap(List<BalanceHistory> balanceHistoryList){
        TreeMap<LocalDate,BalanceHistory>balanceHistoryTreeMap=new TreeMap<>(Comparator.reverseOrder());
        if (balanceHistoryList != null) {
        for (BalanceHistory balanceHistory : balanceHistoryList) {
            balanceHistoryTreeMap.put(balanceHistory.getDate(),balanceHistory);
        }
        }
        return balanceHistoryTreeMap;
    }


    public BalanceHistory getClosestBalanceRecord(TreeMap<LocalDate,BalanceHistory>balanceHistoryTreeMap,LocalDate date) {
        BalanceHistory balanceHistory=null;
        if(balanceHistoryTreeMap!=null){
            for (LocalDate d : balanceHistoryTreeMap.keySet()) {
                if(d.isEqual(date)||d.isBefore(date)){
                    balanceHistory=balanceHistoryTreeMap.get(d);
                    break;
                }
            }
        }
        return balanceHistory;
    }

    public BalanceHistory createBalanceHistory(double balance, LocalDate date, String creditCardNumber) {
        BalanceHistory balanceHistory=new BalanceHistory();
        balanceHistory.setBalance(balance);
        balanceHistory.setDate(date);
        balanceHistory.setCreditCardNumber(creditCardNumber);

        return balanceHistory;
    }


}

