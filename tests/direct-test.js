const mineflayer = require('mineflayer');

const DEFAULT_HOST = process.env.SHARDEDMC_HOST || 'localhost';
const DEFAULT_PORT = parseInt(process.env.SHARDEDMC_PORT, 10) || 25565;

console.log('Testing direct connection to shard...');

const bot = mineflayer.createBot({
    host: DEFAULT_HOST,
    port: DEFAULT_PORT,
    username: 'DirectTest',
    auth: 'offline'
});

bot.on('spawn', () => {
    console.log('✅ Direct connection works!');
    bot.end();
    process.exit(0);
});

bot.on('error', (err) => {
    console.error('❌ Direct failed:', err.message);
    process.exit(1);
});

setTimeout(() => {
    console.error('❌ Direct timeout');
    process.exit(1);
}, 10000);
