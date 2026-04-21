const mineflayer = require('mineflayer');

const timeout = process.env.PROXY_TEST_TIMEOUT ? parseInt(process.env.PROXY_TEST_TIMEOUT) : 10000;
const DEFAULT_HOST = process.env.SHARDEDMC_HOST || 'localhost';
const DEFAULT_PROXY_PORT = parseInt(process.env.SHARDEDMC_PROXY_PORT, 10) || 25577;

console.log(`Testing connection through proxy (${DEFAULT_HOST}:${DEFAULT_PROXY_PORT}) with ${timeout}ms timeout...`);

const bot = mineflayer.createBot({
    host: DEFAULT_HOST,
    port: DEFAULT_PROXY_PORT,
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
