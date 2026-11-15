import test from 'node:test';
import assert from 'node:assert/strict';
import {createRateLimiter} from './rate-limit.mjs';
test('240 sensor/API requests cannot consume the eight authentication attempts',()=>{
 const limited=createRateLimiter();
 for(let i=0;i<240;i++)assert.equal(limited('same-client',240,'api',1000),false);
 assert.equal(limited('same-client',240,'api',1000),true);
 for(let i=0;i<8;i++)assert.equal(limited('same-client',8,'auth',1000),false);
 assert.equal(limited('same-client',8,'auth',1000),true);
});
test('rejected requests do not prolong the lockout beyond the accepted window',()=>{
 const limited=createRateLimiter();
 for(let i=0;i<8;i++)limited('client',8,'auth',0);
 assert.equal(limited('client',8,'auth',59000),true);
 assert.equal(limited('client',8,'auth',60000),false);
});
test('independent clients retain separate limits in the same request category',()=>{
 const limited=createRateLimiter();
 for(let i=0;i<8;i++)limited('one',8,'auth',0);
 assert.equal(limited('one',8,'auth',0),true);
 assert.equal(limited('two',8,'auth',0),false);
});
