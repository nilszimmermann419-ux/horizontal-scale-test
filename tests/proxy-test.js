const mineflayer = require('mineflayer');

const timeout = process.env.PROXY_TEST_TIMEOUT ? parseInt(process.env.PROXY_TEST_TIMEOUT) : 10000;

console.log(`Testing connection through proxy (port 25577) with ${timeout}ms timeout...`);

const bot = mineflayer.createBot({
    host: 'localhost',
    port: 25577,
    username: 'ProxyTest',
    auth: 'offline',
    checkTimeoutInterval: timeout > 10000 ? 120000 : 60000,
    connectTimeout: timeout > 10000 ? 30000 : 10000
});

bot.on('spawn', () => {
    console.log('✅ SUCCESS: Connected through proxy!');
    bot.end();
    process.exit(0);
});

bot.on('error', (err) => {
    console.error('❌ FAILED:', err.message);
    process.exit(1);
});

bot.on('kicked', (reason) => {
    console.error('❌ KICKED:', reason);
    process.exit(1);
});

setTimeout(() => {
    console.error(`❌ FAILED: Connection timeout after ${timeout}ms`);
    process.exit(1);
}, timeout);
