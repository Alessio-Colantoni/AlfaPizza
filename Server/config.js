const config = {

  MONGO_URI: process.env.MONGO_URI,

  PORT: process.env.PORT || 3000

};

module.exports = config;

if (!config.MONGO_URI) {
  throw new Error("ERRORE: MONGO_URI mancante. Configura la variabile d'ambiente MONGO_URI");
}

module.exports = config;
