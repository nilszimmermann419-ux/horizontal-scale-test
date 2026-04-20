const mineflayer = require('mineflayer');

console.log('Testing direct connection to shard...');

const bot = mineflayer.createBot({
    host: 'localhost',
    port: 25565,
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
